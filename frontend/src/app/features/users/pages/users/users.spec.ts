import {ComponentFixture, TestBed} from '@angular/core/testing';
import {HttpErrorResponse} from '@angular/common/http';
import {of, throwError} from 'rxjs';
import {Users} from './users';
import {UsersService} from '../../../../core/services/users.service';
import {provideI18n} from '../../../../../testing/provide-i18n';

describe('Users component', () => {
  let fixture: ComponentFixture<Users>;
  let component: Users;
  let usersService: {
    findPage: ReturnType<typeof vi.fn>;
    findAll: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    const user = {id: 1, email: 'admin@local.dev', firstName: 'Admin', lastName: 'Local', role: 'ADMIN', enabled: true, emailVerified: true};

    usersService = {
      findPage: vi.fn().mockReturnValue(of({items: [user], page: 0, size: 5, totalItems: 1, totalPages: 1})),
      findAll: vi.fn().mockReturnValue(of([user])),
      create: vi.fn(),
      update: vi.fn(),
      delete: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [Users],
      providers: [
        provideI18n(),
        {provide: UsersService, useValue: usersService}
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(Users);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('charge les utilisateurs au démarrage', () => {
    expect(usersService.findPage).toHaveBeenCalledWith({page: 0, size: 5, sort: 'createdAt,desc'});
    expect(component.users().length).toBe(1);
    expect(component.rows[0]['email']).toBe('admin@local.dev');
  });

  it('crée un utilisateur et ajoute la ligne à la liste', () => {
    usersService.create.mockReturnValue(of({
      id: 2,
      email: 'user@local.dev',
      firstName: 'Jane',
      lastName: 'Doe',
      role: 'USER',
      enabled: true,
      emailVerified: false
    }));

    component.startCreate();
    component.form.setValue({
      email: 'user@local.dev',
      firstName: 'Jane',
      lastName: 'Doe',
      password: 'Secret123!',
      role: 'USER',
      enabled: true
    });

    component.submit();

    expect(usersService.create).toHaveBeenCalledWith({
      email: 'user@local.dev',
      firstName: 'Jane',
      lastName: 'Doe',
      password: 'Secret123!',
      role: 'USER',
      enabled: true
    });
    expect(component.users().some(user => user.id === 2)).toBe(true);
    expect(component.infoMessage()).toBe('Utilisateur créé. Un email de vérification a été préparé.');
  });

  it('affiche le message back sur une erreur de création', () => {
    usersService.create.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 409,
      error: {message: 'A user with this email already exists'}
    })));

    component.startCreate();
    component.form.setValue({
      email: 'user@local.dev',
      firstName: 'Jane',
      lastName: 'Doe',
      password: 'Secret123!',
      role: 'USER',
      enabled: true
    });

    component.submit();

    expect(component.errorMessage()).toBe('A user with this email already exists');
  });
});
